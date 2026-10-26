import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function trimmedRequired(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim().length > 0 ? null : { required: true };
}

export function unicodeLength(minimum: number, maximum: number): ValidatorFn {
  return (control) => {
    const value = String(control.value ?? '').trim();
    if (value.length === 0) {
      return null;
    }
    const length = [...value].length;
    return length < minimum || length > maximum
      ? { unicodeLength: { minimum, maximum, actual: length } }
      : null;
  };
}

export function passwordRules(control: AbstractControl<string>): ValidationErrors | null {
  if (control.value.length === 0) {
    return null;
  }
  const codePoints = [...control.value].length;
  const utf8Bytes = new TextEncoder().encode(control.value).length;
  const errors: ValidationErrors = {};
  if (codePoints < 12) {
    errors['minUnicodeLength'] = true;
  }
  if (utf8Bytes > 72) {
    errors['maxUtf8Length'] = true;
  }
  return Object.keys(errors).length > 0 ? errors : null;
}

export function noControlCharacters(control: AbstractControl<string>): ValidationErrors | null {
  return /[\u0000-\u001f\u007f]/u.test(control.value) ? { controlCharacters: true } : null;
}

export function areaLabelRules(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  if (!value) {
    return null;
  }
  const length = [...value].length;
  return length < 2 || length > 120 || /[\u0000-\u001f\u007f]/u.test(value)
    ? { areaLabel: true }
    : null;
}
